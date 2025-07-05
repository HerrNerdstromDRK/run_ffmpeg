package run_ffmpeg.Plex;

import com.google.gson.Gson;

public class Plex_UltraBlurColors
{
	public String topLeft = null ;
	public String topRight = null ;
	public String bottomRight = null ;
	public String bottomLeft = null ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
