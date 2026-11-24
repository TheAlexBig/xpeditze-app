package com.big.dreamer.doccentral.document.mutual.service;

import com.big.dreamer.doccentral.document.carsale.service.CarSaleRequestValidationException;
import com.big.dreamer.doccentral.document.mutual.model.MutualTermMode;
import com.big.dreamer.doccentral.document.mutual.model.MutualTermUnit;
import com.big.dreamer.doccentral.document.mutual.model.MutualTerms;
import com.big.dreamer.doccentral.document.mutual.model.MutualCustomInstallment;
import com.big.dreamer.doccentral.document.mutual.model.MutualPaymentScheduleMode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MutualFinancialCalculator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30");

    public MutualFinancialPlan calculate(MutualTerms terms) {
        BigDecimal capital = positive(terms.numericAmount(), "condiciones.monto_numerico", "El monto debe ser mayor que cero.");
        LocalDate start = required(terms.startDate(), "condiciones.fecha_inicio", "Indique la fecha inicial.");
        if (terms.effectivePaymentScheduleMode() == MutualPaymentScheduleMode.CUSTOM) {
            return customPlan(terms, capital, start);
        }
        int count = positive(terms.numericInstallmentCount(), "condiciones.numero_cuotas_numerico",
                "El número de cuotas debe ser mayor que cero.");
        LocalDate end = resolveEndDate(terms, start);
        long totalDays = ChronoUnit.DAYS.between(start, end);
        if (totalDays < count) {
            throw invalid("condiciones.numero_cuotas_numerico",
                    "El número de cuotas no puede superar los días disponibles.");
        }

        BigDecimal rate = terms.numericMonthlyInterest() == null ? BigDecimal.ZERO : terms.numericMonthlyInterest();
        if (rate.signum() < 0) throw invalid("condiciones.tasa_interes_mensual", "La tasa no puede ser negativa.");
        BigDecimal months = monthEquivalent(terms, totalDays);
        List<LocalDate> dates = paymentDates(terms, start, end, count, totalDays);
        List<MutualFinancialPlan.Installment> installments = amortize(capital, rate, months, dates);
        BigDecimal total = installments.stream()
                .map(installment -> installment.total())
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
        BigDecimal interest = total.subtract(capital);
        return new MutualFinancialPlan(capital, interest, total, count, periodicity(terms, count),
                start, end, List.copyOf(installments));
    }

    private MutualFinancialPlan customPlan(MutualTerms terms, BigDecimal capital, LocalDate start) {
        List<MutualCustomInstallment> custom = terms.customInstallments();
        if (custom == null || custom.isEmpty()) {
            throw invalid("condiciones.cuotas_personalizadas", "Agregue al menos una cuota personalizada.");
        }
        List<MutualFinancialPlan.Installment> installments = new ArrayList<>();
        BigDecimal capitalTotal = BigDecimal.ZERO;
        BigDecimal interestTotal = BigDecimal.ZERO;
        LocalDate previous = start;
        for (int index = 0; index < custom.size(); index++) {
            MutualCustomInstallment item = custom.get(index);
            LocalDate dueDate = required(item.dueDate(), "condiciones.cuotas_personalizadas",
                    "Todas las cuotas deben tener fecha.");
            if (!dueDate.isAfter(previous)) {
                throw invalid("condiciones.cuotas_personalizadas",
                        "Las fechas de las cuotas deben ser posteriores a la firma y estar en orden.");
            }
            BigDecimal installmentCapital = positive(item.capital(), "condiciones.cuotas_personalizadas",
                    "El capital de cada cuota debe ser mayor que cero.");
            BigDecimal installmentInterest = item.interest() == null ? BigDecimal.ZERO : money(item.interest());
            if (installmentInterest.signum() < 0) {
                throw invalid("condiciones.cuotas_personalizadas", "El interés de una cuota no puede ser negativo.");
            }
            capitalTotal = capitalTotal.add(installmentCapital);
            interestTotal = interestTotal.add(installmentInterest);
            installments.add(new MutualFinancialPlan.Installment(index + 1, dueDate,
                    installmentCapital, installmentInterest, installmentCapital.add(installmentInterest)));
            previous = dueDate;
        }
        if (money(capitalTotal).compareTo(capital) != 0) {
            throw invalid("condiciones.cuotas_personalizadas",
                    "La suma del capital de las cuotas debe ser igual al monto mutuado.");
        }
        BigDecimal interest = money(interestTotal);
        return new MutualFinancialPlan(capital, interest, capital.add(interest), installments.size(),
                "Fechas y montos pactados individualmente", start, previous, List.copyOf(installments));
    }

    private LocalDate resolveEndDate(MutualTerms terms, LocalDate start) {
        MutualTermMode mode = required(terms.termMode(), "condiciones.modalidad_plazo", "Seleccione modalidad de plazo.");
        if (mode == MutualTermMode.SPECIFIC_DATE) {
            LocalDate end = required(terms.dueDateIso(), "condiciones.fecha_vencimiento_iso", "Indique fecha de vencimiento.");
            if (!end.isAfter(start)) throw invalid("condiciones.fecha_vencimiento_iso", "Debe ser posterior a la fecha inicial.");
            return end;
        }
        int quantity = positive(terms.termQuantity(), "condiciones.cantidad_plazo", "El plazo debe ser mayor que cero.");
        MutualTermUnit unit = required(terms.termUnit(), "condiciones.unidad_plazo", "Seleccione unidad de plazo.");
        return switch (unit) {
            case DAYS -> start.plusDays(quantity);
            case MONTHS -> start.plusMonths(quantity);
            case YEARS -> start.plusYears(quantity);
        };
    }

    private List<LocalDate> paymentDates(MutualTerms terms, LocalDate start, LocalDate end,
                                         int count, long totalDays) {
        List<LocalDate> dates = new ArrayList<>();
        long months = calendarMonths(terms);
        if (months > 0 && months % count == 0) {
            long step = months / count;
            for (int index = 1; index <= count; index++) dates.add(start.plusMonths(step * index));
        } else {
            for (int index = 1; index <= count; index++) {
                long offset = index == count ? totalDays : totalDays * index / count;
                dates.add(start.plusDays(offset));
            }
        }
        dates.set(count - 1, end);
        return dates;
    }

    private List<MutualFinancialPlan.Installment> amortize(
            BigDecimal capital, BigDecimal monthlyRate, BigDecimal months, List<LocalDate> dates) {
        int count = dates.size();
        BigDecimal periodicRate = monthlyRate.divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP)
                .multiply(months).divide(BigDecimal.valueOf(count), 12, RoundingMode.HALF_UP);
        MathContext precision = new MathContext(16);
        BigDecimal discountFactor = periodicRate.signum() == 0 ? BigDecimal.ZERO
                : BigDecimal.ONE.divide(BigDecimal.ONE.add(periodicRate).pow(count, precision), precision);
        BigDecimal payment = periodicRate.signum() == 0
                ? capital.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : money(capital.multiply(periodicRate).divide(
                        BigDecimal.ONE.subtract(discountFactor), 12, RoundingMode.HALF_UP));
        List<MutualFinancialPlan.Installment> result = new ArrayList<>();
        BigDecimal balance = capital;
        for (int index = 0; index < count; index++) {
            boolean last = index == count - 1;
            BigDecimal installmentInterest = money(balance.multiply(periodicRate));
            BigDecimal installmentCapital = last ? balance : payment.subtract(installmentInterest);
            installmentCapital = money(installmentCapital.min(balance));
            result.add(new MutualFinancialPlan.Installment(index + 1, dates.get(index),
                    installmentCapital, installmentInterest, installmentCapital.add(installmentInterest)));
            balance = balance.subtract(installmentCapital);
        }
        return result;
    }

    private BigDecimal monthEquivalent(MutualTerms terms, long totalDays) {
        if (terms.termMode() == MutualTermMode.DURATION && terms.termUnit() != null) {
            return switch (terms.termUnit()) {
                case DAYS -> BigDecimal.valueOf(terms.termQuantity()).divide(DAYS_PER_MONTH, 12, RoundingMode.HALF_UP);
                case MONTHS -> BigDecimal.valueOf(terms.termQuantity());
                case YEARS -> BigDecimal.valueOf(terms.termQuantity()).multiply(BigDecimal.valueOf(12));
            };
        }
        return BigDecimal.valueOf(totalDays).divide(DAYS_PER_MONTH, 12, RoundingMode.HALF_UP);
    }

    private long calendarMonths(MutualTerms terms) {
        if (terms.termMode() != MutualTermMode.DURATION || terms.termUnit() == null) return 0;
        return switch (terms.termUnit()) {
            case MONTHS -> terms.termQuantity();
            case YEARS -> terms.termQuantity() * 12L;
            case DAYS -> 0;
        };
    }

    private String periodicity(MutualTerms terms, int count) {
        if (terms.termMode() == MutualTermMode.DURATION && terms.termQuantity() % count == 0) {
            int value = terms.termQuantity() / count;
            String unit = switch (terms.termUnit()) {
                case DAYS -> value == 1 ? "día" : "días";
                case MONTHS -> value == 1 ? "mes" : "meses";
                case YEARS -> value == 1 ? "año" : "años";
            };
            return "Cada " + value + " " + unit;
        }
        return "Fechas distribuidas proporcionalmente hasta el vencimiento";
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal positive(BigDecimal value, String field, String message) {
        if (value == null || value.signum() <= 0) throw invalid(field, message);
        return money(value);
    }

    private int positive(Integer value, String field, String message) {
        if (value == null || value <= 0) throw invalid(field, message);
        return value;
    }

    private <T> T required(T value, String field, String message) {
        if (value == null) throw invalid(field, message);
        return value;
    }

    private CarSaleRequestValidationException invalid(String field, String message) {
        return new CarSaleRequestValidationException("Revise las condiciones del mutuo.", Map.of(field, message));
    }
}
