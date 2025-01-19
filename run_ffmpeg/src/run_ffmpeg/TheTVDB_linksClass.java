package run_ffmpeg;

import com.google.gson.Gson;

public class TheTVDB_linksClass
{
	public String prev = "" ;
	public String self = "" ;
	public String next = "" ;
	public Integer total_items = null ;
	public Integer page_size = null ;
	
	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}
