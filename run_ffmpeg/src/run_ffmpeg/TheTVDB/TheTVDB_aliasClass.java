package run_ffmpeg.TheTVDB;

import com.google.gson.Gson;

public class TheTVDB_aliasClass
{
	public String language = "" ;
	public String name = "" ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
