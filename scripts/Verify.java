import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class Verify {
    private Verify() {
    }

    public static void main(String[] testClasses) throws IOException, InterruptedException {
        Path repository = Path.of("").toAbsolutePath().normalize();
        if (!Files.isRegularFile(repository.resolve("pom.xml"))) {
            System.err.println("Ejecute este script desde la raíz de xpeditze-app.");
            System.exit(2);
        }

        List<String> command = new ArrayList<>();
        command.add(isWindows() ? "mvn.cmd" : "mvn");
        if (testClasses.length > 0) {
            command.add("-Dtest=" + String.join(",", testClasses));
        }
        command.add("test");
        command.add("spotbugs:check");

        Instant startedAt = Instant.now();
        Process process = new ProcessBuilder(command)
                .directory(repository.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int status = process.waitFor();
        double seconds = Duration.between(startedAt, Instant.now()).toMillis() / 1_000.0;

        if (status != 0) {
            Path log = Files.createTempFile("xpeditze-app-verificacion-", ".log");
            Files.writeString(log, output, StandardCharsets.UTF_8);
            System.err.printf("FALLÓ: pruebas o análisis estático backend (%.1f s)%n", seconds);
            System.err.println(lastLines(output, 25));
            System.err.println("Registro completo: " + log);
            System.exit(status);
        }

        System.out.printf("OK: pruebas y análisis estático backend (%.1f s).%n", seconds);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static String lastLines(String output, int count) {
        String[] lines = output.stripTrailing().split("\\R");
        return String.join(System.lineSeparator(),
                List.of(lines).subList(Math.max(0, lines.length - count), lines.length));
    }
}
