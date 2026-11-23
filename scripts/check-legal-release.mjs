import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const applicationDirectory = path.resolve(scriptDirectory, '..');
const approvalsPath = path.join(
  applicationDirectory,
  'legal-release-approvals.json'
);
const templatesDirectory = path.join(
  applicationDirectory,
  'src',
  'main',
  'resources',
  'document-templates'
);

const fail = (messages) => {
  for (const message of messages) process.stderr.write(`ERROR: ${message}\n`);
  process.exitCode = 1;
};

let approvals;
try {
  approvals = JSON.parse(fs.readFileSync(approvalsPath, 'utf8'));
} catch (error) {
  fail([`No se pudo leer ${approvalsPath}: ${error.message}`]);
  process.exit();
}

const errors = [];
if (approvals.schemaVersion !== 1) {
  errors.push('legal-release-approvals.json debe usar schemaVersion 1.');
}

const documentTypes = fs
  .readdirSync(templatesDirectory, { withFileTypes: true })
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name)
  .sort();

for (const documentType of documentTypes) {
  const manifestPath = path.join(
    templatesDirectory,
    documentType,
    'manifest.json'
  );
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  const approval = approvals.documents?.[documentType];

  if (!approval) {
    errors.push(`${documentType}: falta el registro de aprobación legal.`);
    continue;
  }
  if (approval.templateVersion !== manifest.version) {
    errors.push(
      `${documentType}: la aprobación corresponde a la plantilla v${approval.templateVersion}, ` +
        `pero el manifiesto usa v${manifest.version}.`
    );
  }
  if (approval.status !== 'approved') {
    errors.push(`${documentType}: la revisión legal está pendiente.`);
  }
  if (!approval.reviewedBy?.trim()) {
    errors.push(`${documentType}: falta reviewedBy.`);
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(approval.reviewedAt || '')) {
    errors.push(`${documentType}: reviewedAt debe usar el formato AAAA-MM-DD.`);
  }
}

for (const documentType of Object.keys(approvals.documents || {})) {
  if (!documentTypes.includes(documentType)) {
    errors.push(`${documentType}: no existe un catálogo de plantillas asociado.`);
  }
}

if (errors.length > 0) {
  fail(errors);
} else {
  process.stdout.write('OK: aprobación legal vigente para todos los documentos.\n');
}
