package run_ffmpeg.TheTVDB;

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
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
