package run_ffmpeg.Plex;

import com.google.gson.Gson;

public class Plex_MediaContainerWrapper
{
	public Plex_MediaContainer MediaContainer = null ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
