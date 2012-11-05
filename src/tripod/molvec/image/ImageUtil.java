package tripod.molvec.image;

import java.io.File;
import java.io.IOException;
import java.awt.*;
import java.awt.color.*;
import java.awt.image.*;

import javax.imageio.*;

import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.media.jai.codec.*;


public class ImageUtil implements TiffTags {
    private static final Logger logger = Logger.getLogger
	(ImageUtil.class.getName());
    
    public static BufferedImage decode (File file) throws IOException {
	ImageDecoder decoder = ImageCodec.createImageDecoder
	    ("TIFF", file, new TIFFDecodeParam ());

	int ndirs = decoder.getNumPages();

	TIFFDirectory tif = new TIFFDirectory 
	    (decoder.getInputStream(), 0);
	TIFFField[] fields = tif.getFields();

	double width = 0, height = 0;
	String unit = "";
	double xres = 0., yres = 0.;
	int rows = -1, photometric = -1, bpp = -1;
	for (int j = 0; j < fields.length; ++j) {
	    TIFFField f = fields[j];
	    int tag = f.getTag();
	    switch (tag) {
	    case TAG_RESOLUTIONUNIT:
		{
		    int u = f.getAsInt(0);
		    if (u == RESOLUTIONUNIT_NONE) {
		    }
		    else if (u == RESOLUTIONUNIT_INCH) {
			unit = "in";
		    }
		    else if (u == RESOLUTIONUNIT_CENT) {
			unit = "cm";
		    }
		}
		break;
		
	    case TAG_XRESOLUTION:
		xres = f.getAsFloat(0);
		break;

	    case TAG_YRESOLUTION:
		yres = f.getAsFloat(0);
		break;

	    case TAG_ROWSPERSTRIP:
		rows = f.getAsInt(0);
		break;

	    case TAG_PHOTOMETRIC:
		photometric = f.getAsInt(0);
		break;

	    case TAG_BITSPERSAMPLE:
		bpp = f.getAsInt(0);
		break;

	    case TAG_IMAGEWIDTH: 
		width = f.getAsFloat(0);
		break;

	    case TAG_IMAGELENGTH:	
		height = f.getAsFloat(0);
		break;
	    }
	}

	/*
	if (xres > 0) {
	    width /= xres;
	}
	if (yres > 0) {
	    height /= yres;
	}
	*/

	RenderedImage decodedImage = decoder.decodeAsRenderedImage();
	Raster raster = decodedImage.getData();
	/*
	if (raster.getNumBands() > 1) {
	    throw new IllegalArgumentException 
		("Sorry, can't support multiband image at the moment!");
	}
	*/

	logger.info(file + " has " + ndirs + " image; width="+width+" height="+height+" nbands="+raster.getNumBands()+" xres="+xres+unit+" yres="+yres+unit+" bpp="+bpp+" photometric="+photometric+" rows="+rows);

	logger.info("sample model: "+raster.getSampleModel().getClass());
	MultiPixelPackedSampleModel packed = 
	    (MultiPixelPackedSampleModel)raster.getSampleModel();
	logger.info("scanline: "+packed.getScanlineStride());
	logger.info("bit stride: "+packed.getPixelBitStride());

	int max = 0;
	int min = Integer.MAX_VALUE;
	for (int i = 0; i < raster.getWidth(); ++i) {
	    for (int j = 0; j < raster.getHeight(); ++j) {
		int pixel = raster.getSample(i, j, 0);
		if (pixel > max) max = pixel;
		if (pixel < min) min = pixel;
	    }
	}

	// rescale to 8-bit
	double scale = 256./(max-min+1);
	RescaleOp rescale = new RescaleOp 
	    ((float)scale, (float)-scale*min, null);
	Raster scaled = rescale.filter(raster, null);
	BufferedImage image = new BufferedImage 
	    (raster.getWidth(), raster.getHeight(), 
	     BufferedImage.TYPE_BYTE_GRAY);
	image.setData(scaled);

	return image;
    }
    public static BufferedImage decodeAny (BufferedImage bi){
    	BufferedImage gImage = new BufferedImage 
        	    (bi.getWidth(), bi.getHeight(), 
        	     BufferedImage.TYPE_BYTE_GRAY);
    	Graphics graph = gImage.getGraphics();
    	graph.setColor(Color.white);
    	graph.fillRect(0,0, bi.getWidth(), bi.getHeight());
    	
    	gImage.getGraphics().drawImage(bi, 0, 0, null);
    	Raster raster = gImage.getData();
    	
    	int max = 0;
    	int min = Integer.MAX_VALUE;
    	for (int i = 0; i < raster.getWidth(); ++i) {
    	    for (int j = 0; j < raster.getHeight(); ++j) {
	    		int pixel = raster.getSample(i, j, 0);
	    		//System.out.print(pixel%10);
	    		if (pixel > max) max = pixel;
	    		if (pixel < min) min = pixel;
    	    }
    	    //System.out.println();
    	}

    	// rescale to 8-bit
    	double scale = Math.max(256./(max-min+1),1);
    	RescaleOp rescale = new RescaleOp 
    	    ((float)scale, -(float)scale*min, null);
    	Raster scaled = rescale.filter(raster, null);
    	rescale = new RescaleOp 
        	    (-1, 255, null);
        scaled = rescale.filter(scaled, null);
        	
    	BufferedImage image = new BufferedImage 
    	    (raster.getWidth(), raster.getHeight(), 
    	     BufferedImage.TYPE_BYTE_GRAY);
    	
    	image.setData(scaled);
    	return image;
    }
    public static BufferedImage decodeAny (File file) throws IOException {
    	return decodeAny(ImageIO.read(file));
    }

    public static BufferedImage fuse (BufferedImage img0, BufferedImage img1) {
	if (img0.getWidth() != img1.getWidth() 
	    || img0.getHeight() != img1.getHeight()) {
	    throw new IllegalArgumentException 
		("Images are not of the same dimension");
	}

	int width = img0.getWidth(), height = img0.getHeight();

	BufferedImage fused = new BufferedImage 
	    (width, height, BufferedImage.TYPE_INT_RGB);

	/*
	BufferedImage green = new BufferedImage 
	    (width, height, BufferedImage.TYPE_INT_RGB);
	BufferedImage blue = new BufferedImage 
	    (width, height, BufferedImage.TYPE_INT_RGB);
	for (int x = 0; x < width; ++x) {
	    for (int y = 0; y < height; ++y) {
		green.setRGB(x, y, (img0.getRGB(x,y) & 0xff) << 8);
		blue.setRGB(x, y, grayscale(img1.getRGB(x,y)) & 0xff);
	    }
	}
	
	Graphics2D g = fused.createGraphics();
	g.drawImage(green, 0, 0, null);
	AlphaComposite c = AlphaComposite.getInstance
	    (AlphaComposite.SRC_OVER, .35f);
	g.setComposite(c);
	g.drawImage(blue, 0, 0, null);
	g.dispose();
	*/	
	int p, q, g, b;
	for (int x = 0; x < width; ++x) {
	    for (int y = 0; y < height; ++y) {
		p = img0.getRGB(x, y) & 0xff00;
		q = img1.getRGB(x, y) & 0xff;
		fused.setRGB(x, y, p | q);
	    }
	}

	return fused;
    }

    protected static int grayscale (int rgb) {
	int r = (rgb & 0x00ffffff) >> 16;
	int g = (rgb & 0x0000ffff) >> 8;
	int b = (rgb & 0x000000ff);
	/*
	return (int)(0.3*r + 0.59*g + 0.11*b + 0.5);
	*/
	return Math.max(r, Math.max(g, b));
    }

    public static void main (String[] argv) throws Exception {
	if (argv.length == 0) {
	    System.out.println("Usage: ImageUtil IMAGE.tif");
	    System.exit(1);
	}

	BufferedImage img = decode (new File (argv[0]));
    }
}