package run_ffmpeg.Plex;

import com.google.gson.Gson;

public class Plex_Filter
{
	public String filter = null ;
	public String filterType = null ;
	public String key = null ;
	public String title = null ;
	public String type = null ;
	public Boolean advanced = null ;

	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
