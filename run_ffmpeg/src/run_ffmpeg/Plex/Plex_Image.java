package run_ffmpeg.Plex;

import com.google.gson.Gson;

public class Plex_Image
{
	public String alt = null ;
	public String type = null ;
	public String url = null ;

	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
