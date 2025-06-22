package run_ffmpeg.Plex;

import com.google.gson.Gson;

public class plexAPI_Field
{
	public String key = null ;
	public String title = null ;
	public String type = null ;
	public String subType = null ;

	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
