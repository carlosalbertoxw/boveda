// Escribe un respaldo de Boveda (formato version 2) SIN usar el codigo Kotlin,
// directamente desde la especificacion del formato. Sirve de prueba de
// compatibilidad: las dos apps leen y escriben con el mismo Respaldo.kt, asi que
// solo un archivo escrito por otro programa demuestra que ese codigo sigue la
// especificacion y no se desvio de ella sin que nada lo notara.
//
//   node herramientas/respaldo-referencia.mjs > src/test/resources/respaldo-referencia-v2.json
//
// Se lanza desde boveda-nucleo/.
import crypto from "node:crypto";

const contrasena = "contraseña del respaldo 🔐";
const iteraciones = 210000;

// Kdf.kt: UTF-8 de la contrasena, cada byte reexpandido a un char de 8 bits, y
// PBEKeySpec vuelve a codificar esos chars en UTF-8 (OpenJDK y BouncyCastle de
// Android hacen lo mismo). En bytes: UTF-8 aplicado dos veces.
const bytesClave = Buffer.from(Buffer.from(contrasena, "utf8").toString("latin1"), "utf8");

const sal = Buffer.from(Array.from({ length: 16 }, (_, i) => (i * 7 + 3) & 0xff));
const salB64 = sal.toString("base64");
const llave = crypto.pbkdf2Sync(bytesClave, sal, iteraciones, 32, "sha256");

const cuerpo = {
  credenciales: [
    {
      titulo: "Banco", usuario: "carlos@ejemplo.mx", contrasena: "N0-la-adivinas #2026",
      sitio: "banco.mx", notas: "Cuenta de nómina", creadoEn: 1700000000000, actualizadoEn: 1700000500000,
      historial: [
        { contrasena: "N0-la-adivinas #2026", creadoEn: 1700000500000, actual: true },
        { contrasena: "la vieja del año pasado", creadoEn: 1600000000000, actual: false },
      ],
    },
    {
      titulo: "Correo", usuario: "carlos", contrasena: "otra distinta", sitio: "", notas: "",
      creadoEn: 1700000100000, actualizadoEn: 1700000100000, eliminadoEn: 1700090000000,
      historial: [{ contrasena: "otra distinta", creadoEn: 1700000100000, actual: true }],
    },
  ],
};

const aad = Buffer.from(`boveda-respaldo|2|PBKDF2WithHmacSHA256|${iteraciones}|${salB64}|AES-256-GCM`, "utf8");
const iv = Buffer.from(Array.from({ length: 12 }, (_, i) => (i * 11 + 5) & 0xff));
const cifrador = crypto.createCipheriv("aes-256-gcm", llave, iv);
cifrador.setAAD(aad);
const cifrado = Buffer.concat([cifrador.update(JSON.stringify(cuerpo), "utf8"), cifrador.final(), cifrador.getAuthTag()]);

const sobre = {
  formato: "boveda-respaldo",
  version: 2,
  creado: "2026-08-15T18:00:00Z",
  entradas: 2,
  kdf: { algoritmo: "PBKDF2WithHmacSHA256", iteraciones, sal: salB64 },
  cifrado: "AES-256-GCM",
  datos: Buffer.concat([iv, cifrado]).toString("base64"),
};
process.stdout.write(JSON.stringify(sobre, null, 2) + "\n");
