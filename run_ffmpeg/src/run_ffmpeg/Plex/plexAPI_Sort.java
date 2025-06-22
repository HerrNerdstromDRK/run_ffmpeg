package run_ffmpeg.Plex;

import com.google.gson.Gson;

public class plexAPI_Sort
{
	public Boolean active = null ;
	public String activeDirection = null ;
	public String defaultDirection = null ;
	public String firstCharacterKey = null ;
	public String key = null ;
	public String title = null ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
