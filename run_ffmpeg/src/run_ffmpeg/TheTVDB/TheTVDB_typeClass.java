package run_ffmpeg.TheTVDB;

import com.google.gson.Gson;

public class TheTVDB_typeClass
{
	public String alternateName = "" ;
	public Integer id = null ;
	public String name = "" ;
	public String type = "" ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
