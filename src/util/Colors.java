package util;

import javafx.scene.paint.Color;
import json.DataFormatException;
import json.parser.PosBuffer;

/**
 * @author Sebastian Stern
 */
public class Colors {
    public final static Color BLUE = Color.rgb(115, 176, 194);

	public final static Color PETROL = Color.rgb(3, 127, 127);

	public final static Color GREEN = Color.rgb(102, 255, 0);

	public final static Color CURRY = Color.rgb(203, 147, 0);

	public final static Color RED = Color.rgb(255, 31, 31);

	private final static Color TEAM1 = Color.rgb(93, 78, 111);

	private final static Color TEAM2 = Color.rgb(97, 106, 41);

    public static Color getReadable(final Color color) {
		double r = color.getRed() ;
		double g = color.getGreen();
		double b = color.getBlue();

		double luminance=0.299*r + 0.587*g + 0.114*b;
		return luminance>=.5?Color.BLACK:Color.WHITE;
	}

	public static Color getRandom() {
		return Color.color(Math.random(), Math.random(), Math.random());
	}

	public static Color team(boolean first) {
		return first ? TEAM1 : TEAM2;
	}

    public static java.awt.Color getReadable(final java.awt.Color color) {
		return toAwt(getReadable(toFx(color)));
	}
    public static java.awt.Color toAwt(Color c){
    	return new java.awt.Color((float)c.getRed(), (float)c.getGreen(), (float)c.getBlue(), (float)c.getOpacity());
    }
    public static Color toFx(java.awt.Color c){
    	double factor=1.0/0xFF;
    	return Color.rgb(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()*factor);
    }
    public static String toString(Color c){
    	return toString(toAwt(c));
    }
    public static String toString(java.awt.Color c){
    	return c.getRed()+"/"+c.getGreen()+"/"+c.getBlue();
    }
	public static Color parseColor(String s, PosBuffer pos) {
		int firstSlash=s.indexOf('/');
		int lastSlash=s.lastIndexOf('/');
		if(firstSlash!=lastSlash && firstSlash>=0 && lastSlash>=0){
			try{
				int r = Integer.parseInt(s.substring(0, firstSlash));
				int g = Integer.parseInt(s.substring(firstSlash+1, lastSlash));
				int b = Integer.parseInt(s.substring(lastSlash+1));
				if(r<0 || g<0 || b<0 || r>0xFF || g>0xFF || b>0xFF)
					throw new DataFormatException("invalid color string: Number out of range", pos);
				Color color = Color.rgb(r, g, b);
				return color;
			}catch(NumberFormatException x){
				throw new DataFormatException("invalid color string", pos);
			}

		}
		if(s.length()==6 && s.matches("[0-9a-fA-F]*")){
			int r = Integer.parseInt(s.substring(0, 2), 16);
			int g = Integer.parseInt(s.substring(2, 4), 16);
			int b = Integer.parseInt(s.substring(4, 6), 16);
			Color color = Color.rgb(r, g, b);
			return color;

		}
		throw new DataFormatException("invalid color string", pos);
	}
}
