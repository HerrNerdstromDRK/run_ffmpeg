package run_ffmpeg.Plex;

import com.google.gson.Gson;

public class plexAPI_Collection
{
	public String tag = null ;
	
	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}
