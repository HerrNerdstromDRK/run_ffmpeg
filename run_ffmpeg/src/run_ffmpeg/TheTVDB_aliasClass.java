package run_ffmpeg;

import com.google.gson.Gson;

public class TheTVDB_aliasClass
{
	public String language = "" ;
	public String name = "" ;
	
	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}
