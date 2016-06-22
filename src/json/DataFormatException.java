package json;

import json.parser.PosBuffer;

public class DataFormatException extends RuntimeException {
	private static final long serialVersionUID = 4233201338552028648L;

	PosBuffer pos;
	public DataFormatException(String message, PosBuffer pos){
		super(message);
		this.pos=pos.clone();
	}
	
	public PosBuffer getPosition(){
		return pos;
	}
	public String toString(){
		printStackTrace();
		return getClass().getName()+(pos==null?"":": At "+pos)+": "+getMessage();
	}
}