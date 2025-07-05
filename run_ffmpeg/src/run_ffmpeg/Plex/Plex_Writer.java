package run_ffmpeg.Plex;

import com.google.gson.Gson;

public class Plex_Writer
{
	public String tag = null ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
