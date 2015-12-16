package gr.ntua.image.mediachecker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.awt.image.BufferedImage;

import org.apache.tika.Tika;
import org.json.JSONObject;

import org.im4java.core.Info;
import org.im4java.core.InfoException;
import org.im4java.core.IMOperation;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.process.ArrayListOutputConsumer;

import com.xuggle.xuggler.Global;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.Matrix;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import com.itextpdf.text.pdf.parser.ImageRenderInfo;
import com.itextpdf.text.pdf.parser.SimpleTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.TextExtractionStrategy;
import com.itextpdf.text.pdf.parser.RenderListener;
import com.itextpdf.text.pdf.parser.TextRenderInfo;

public class MediaChecker {

	private static final String version = "1.0.0";
	private static final int PALETTE_SIZE = 6;

	/**
	 * Static function to get basic information about an image file
	 * @param  filename              filename of the query image
	 * @param  colormap              filename of the colormap to be used for creating the color palette
	 * @return                       an ImageInfo object with the results
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws InfoException
	 */
	public static ImageInfo getImageInfo(String filename, String colormap) throws IOException, FileNotFoundException, InfoException, InterruptedException, IM4JavaException {
		Info ii           = new Info(filename, true);
		int width         = ii.getImageWidth();
		int height        = ii.getImageHeight();
		String format     = ii.getImageFormat();
		String mimeType   = MediaChecker.getMimeType(filename);

		ii                = new Info(filename);
		String colorSpace = ii.getProperty("Colorspace");

		// Get 6 dominant colors from the image
		ConvertCmd cmd                 = new ConvertCmd();
		IMOperation op                 = new IMOperation();
		ArrayListOutputConsumer output = new ArrayListOutputConsumer();
		op.addImage(filename);
		op.dither("Riemersma");
		op.remap(colormap);
		op.format("%c");
		op.addImage("histogram:info:-");
		cmd.setOutputConsumer(output);
		cmd.run(op);

		// Parse output
		ArrayList<String> lines = output.getOutput();
		Collections.sort(lines);
		Collections.reverse(lines);
		Pattern p = Pattern.compile("#(?:[0-9a-fA-F]{6}){1,2}");
		ArrayList<String> colors = new ArrayList<String>();

		for (String line : lines) {
			Matcher m = p.matcher(line);
			if (m.find()) {
				String clr = m.group(0).substring(1);
				colors.add(m.group(0));

				if (colors.size() >= PALETTE_SIZE)
					break;
			}
		}

		String[] palette = new String[colors.size()];
		colors.toArray(palette);

		return new ImageInfo(width, height, mimeType, format, colorSpace, palette);
	}

	/**
	 * Static function to get basic information about a video file
	 * @param  filename                 filename of the video file
	 * @return                          a VideoInfo object with the results
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws IllegalArgumentException
	 */
	public static VideoInfo getVideoInfo(String filename) throws IOException, FileNotFoundException, IllegalArgumentException {
		IContainer container = IContainer.make();
		if (container.open(filename, IContainer.Type.READ, null) < 0)
			throw new IllegalArgumentException("Could not open file: " + filename);

		String mimeType  = MediaChecker.getMimeType(filename);
		int numStreams   = container.getNumStreams();
		long duration    = container.getDuration() == Global.NO_PTS ? -1 : container.getDuration() / 1000;
		int width        = 0;
		int height       = 0;
		double framerate = 0.0;
		int bitRate      = 0;
		String codec     = "";

		for (int i=0; i<numStreams; i++) {
			IStream stream     = container.getStream(i);
			IStreamCoder coder = stream.getStreamCoder();
			if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO) {
				width     = coder.getWidth();
				height    = coder.getHeight();
				framerate = coder.getFrameRate().getDouble();
				codec     = coder.getCodecID().toString();
				bitRate   = coder.getBitRate();
				break;
			}
		}

		container.close();

		return new VideoInfo(width, height, mimeType, codec.substring(9).toLowerCase(), duration, framerate, bitRate);
	}

	/**
	 * Static function to get basic information about an audio file
	 * @param  filename                 filename of the audio file
	 * @return                          an AudioInfo object with the results
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws IllegalArgumentException
	 */
	public static AudioInfo getAudioInfo(String filename) throws IOException, FileNotFoundException, IllegalArgumentException {
		IContainer container = IContainer.make();
		if (container.open(filename, IContainer.Type.READ, null) < 0)
			throw new IllegalArgumentException("Could not open file: " + filename);

		String mimeType = MediaChecker.getMimeType(filename);
		int numStreams  = container.getNumStreams();
		long duration   = container.getDuration() == Global.NO_PTS ? -1 : container.getDuration() / 1000;
		int channels    = 0;
		int sampleRate  = 0;
		int bitDepth    = 0;
		int bitRate     = 0;
		String codec    = "";

		for (int i=0; i<numStreams; i++) {
			IStream stream     = container.getStream(i);
			IStreamCoder coder = stream.getStreamCoder();
			if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO) {
				channels   = coder.getChannels();
				sampleRate = coder.getSampleRate();
				bitDepth   = Integer.parseInt(coder.getSampleFormat().toString().substring(5));
				bitRate    = coder.getBitRate();
				codec      = coder.getCodecID().toString();
				break;
			}
		}

		return new AudioInfo(mimeType, codec.substring(9).toLowerCase(), duration, sampleRate, bitDepth, bitRate, channels);
	}

	/**
	 * Function to determine if a text file is searchable.
	 * XML, RTF, ePub and text files are by definition searchable. A PDF file is considered searchable if it contains
	 * ANY text on any page.
	 * @param  filename              file to be checked
	 * @return
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	protected static boolean isSearchable(String filename) throws IOException, FileNotFoundException {
		String mimeType = MediaChecker.getMimeType(filename);

		switch (mimeType) {
			case "application/xml":
			case "application/rtf":
			case "application/epub":
			case "text/plain":
				return true;
			case "application/pdf":
				PdfReader reader              = new PdfReader(filename);
				PdfReaderContentParser parser = new PdfReaderContentParser(reader);
				boolean searchable            = false;

				String page;
				TextExtractionStrategy strategy;
				for (int i=1; i<=reader.getNumberOfPages(); i++) {
					strategy = parser.processContent(i, new SimpleTextExtractionStrategy());

					page = strategy.getResultantText();
					if (page != null && !page.isEmpty()) {
						searchable = true;
						break;
					}
				}
				reader.close();

				return searchable;
			default:
				return false;
		}
	}

	protected static int getDPI(String filename) throws IOException, FileNotFoundException {
		PdfReader reader = new PdfReader(filename);
		PdfReaderContentParser parser = new PdfReaderContentParser(reader);
		ImageParsingListener listener = new ImageParsingListener();

		int dpi = 0;
		for (int i=1; i<=reader.getNumberOfPages(); i++) {
			parser.processContent(i, listener);

			// On the second image we come accross, we break out of the loop and return -1.
			if (dpi > 0 && listener.getDPI() > 0) {
				return -1;
			}

			dpi = listener.getDPI();
		}

		return dpi;
	}

	/**
	 * Function to get the mime-type of a file.
	 * @param  filename              name of the file to be checked
	 * @return                       the mime-type
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static String getMimeType(String filename) throws IOException, FileNotFoundException {
		File file       = new File(filename);
		InputStream is  = new FileInputStream(file);
		String mimeType = new Tika().detect(file);
		is.close();

		return mimeType;
	}

	/**
	 * Function to get the size of a file in bytes
	 * @param  filename              name of the file to be checked
	 * @return
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static long getFileSize(String filename) throws IOException, FileNotFoundException {
		File file = new File(filename);
		return file.length();
	}

	/**
	 * Function to get the version of MediaChecker
	 * @return
	 */
	public static String getVersion() {
		return MediaChecker.version;
	}

	private static class ImageParsingListener implements RenderListener {

		protected int dpi;

		public int getDPI() {
			return dpi;
		}

		@Override
		public void beginTextBlock() {
		}

		@Override
		public void renderText(TextRenderInfo tri) {
		}

		@Override
		public void endTextBlock() {
		}

		@Override
		public void renderImage(ImageRenderInfo iri) {
			try {
				BufferedImage image = iri.getImage().getBufferedImage();
				int wPx = image.getWidth();
				int hPx = image.getHeight();

				Matrix m = iri.getImageCTM();
				float wInch = m.get(Matrix.I11) / 72;
				float hInch = m.get(Matrix.I22) / 72;

				int xDPI = Math.abs(Math.round(wPx / wInch));
				int yDPI = Math.abs(Math.round(hPx / hInch));

				dpi = Math.min(xDPI, yDPI);
			} catch (IOException ex) {
				System.err.println(ex.getMessage());
			}


		}
	}

	// public static void main(String args[]) {
	// 	if (args.length < 2) {
	// 		System.out.println("No input file!");
	// 		return;
	// 	}

	// 	try {
	// 		if (args[0].equals("-b")) {
	// 			BufferedReader br = new BufferedReader(new FileReader(args[1]));

	// 			for (String line ; (line = br.readLine()) != null ;) {
	// 				ImageInfo ii = MediaChecker.getImageInfo(line, "colormap.png");
	// 				JSONObject jo = ii.toJSON();
	// 				jo.put("filesize", getFileSize(line));
	// 				jo.put("version", getVersion());

	// 				PrintWriter out = new PrintWriter(line + ".json");
	// 				out.println(jo.toString());
	// 				out.close();
	// 			}

	// 			br.close();
	// 		}
	// 		else if (args[0].equals("-i")) {
	// 			ImageInfo ii = MediaChecker.getImageInfo(args[1], "colormap.png");
	// 			JSONObject jo = ii.toJSON();
	// 			jo.put("filesize", getFileSize(args[1]));
	// 			jo.put("version", getVersion());

	// 			PrintWriter out = new PrintWriter(args[1] + ".json");
	// 			out.println(jo.toString());
	// 			out.close();
	// 		}
	// 	} catch (Exception e) {
	// 		System.out.println(e.getMessage());
	// 		return;
	// 	}
	// }
}
