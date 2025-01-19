package run_ffmpeg;

import com.google.gson.Gson;

public class TheTVDB_typeClass
{
	public String alternateName = "" ;
	public Integer id = null ;
	public String name = "" ;
	public String type = "" ;
	
	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}
