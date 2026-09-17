import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Dibuja el escudo de la app —el mismo del icono de Android— y escribe los tres
 * formatos que piden los instaladores: .ico (Windows), .icns (macOS) y .png
 * (Linux). Sin dependencias, con el JDK:
 *
 *   java herramientas/GeneraIconos.java
 *
 * Los archivos resultantes se versionan en iconos/, asi que solo hace falta
 * volver a correrlo si cambia el dibujo.
 */
public class GeneraIconos {

    public static void main(String[] args) throws Exception {
        Path salida = Path.of("iconos");
        Files.createDirectories(salida);

        Files.write(salida.resolve("boveda.png"), png(512));

        // ICO con PNG embebido: lo aceptan Windows Vista en adelante y WiX.
        int[] tamanosIco = {16, 24, 32, 48, 64, 128, 256};
        List<byte[]> imagenes = new ArrayList<>();
        for (int t : tamanosIco) imagenes.add(png(t));
        ByteBuffer cabecera = ByteBuffer.allocate(6 + 16 * tamanosIco.length).order(ByteOrder.LITTLE_ENDIAN);
        cabecera.putShort((short) 0).putShort((short) 1).putShort((short) tamanosIco.length);
        int desplazamiento = cabecera.capacity();
        for (int i = 0; i < tamanosIco.length; i++) {
            int t = tamanosIco[i];
            cabecera.put((byte) (t >= 256 ? 0 : t)).put((byte) (t >= 256 ? 0 : t));
            cabecera.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
            cabecera.putInt(imagenes.get(i).length).putInt(desplazamiento);
            desplazamiento += imagenes.get(i).length;
        }
        ByteArrayOutputStream ico = new ByteArrayOutputStream();
        ico.write(cabecera.array());
        for (byte[] imagen : imagenes) ico.write(imagen);
        Files.write(salida.resolve("boveda.ico"), ico.toByteArray());

        // ICNS con PNG embebido: ic07 = 128, ic08 = 256, ic09 = 512, ic10 = 1024.
        String[] tipos = {"ic07", "ic08", "ic09", "ic10"};
        int[] tamanosIcns = {128, 256, 512, 1024};
        ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
        DataOutputStream datos = new DataOutputStream(cuerpo);
        for (int i = 0; i < tipos.length; i++) {
            byte[] imagen = png(tamanosIcns[i]);
            datos.writeBytes(tipos[i]);
            datos.writeInt(8 + imagen.length);
            datos.write(imagen);
        }
        ByteArrayOutputStream icns = new ByteArrayOutputStream();
        DataOutputStream archivo = new DataOutputStream(icns);
        archivo.writeBytes("icns");
        archivo.writeInt(8 + cuerpo.size());
        archivo.write(cuerpo.toByteArray());
        Files.write(salida.resolve("boveda.icns"), icns.toByteArray());

        System.out.println("Iconos escritos en " + salida.toAbsolutePath());
    }

    /** El escudo sobre fondo redondeado, en un lienzo de 108 unidades como el vector de Android. */
    private static byte[] png(int lado) throws Exception {
        BufferedImage imagen = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        var g = imagen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(lado / 108.0, lado / 108.0);

        g.setColor(new Color(0x1B2A4A));
        g.fill(new RoundRectangle2D.Double(6, 6, 96, 96, 24, 24));

        Path2D escudo = new Path2D.Double();
        escudo.moveTo(54, 20);
        escudo.lineTo(78, 29.6);
        escudo.lineTo(78, 51.2);
        escudo.curveTo(78, 65.6, 68.4, 77, 54, 82.4);
        escudo.curveTo(39.6, 77, 30, 65.6, 30, 51.2);
        escudo.lineTo(30, 29.6);
        escudo.closePath();
        g.setColor(new Color(0x7FB3FF));
        g.fill(escudo);

        g.setColor(new Color(0x12131A));
        g.fill(new Ellipse2D.Double(46.8, 35.6, 14.4, 14.4));
        g.fill(new Rectangle2D.Double(50.4, 47.6, 7.2, 16.8));
        g.dispose();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", bytes);
        return bytes.toByteArray();
    }
}
