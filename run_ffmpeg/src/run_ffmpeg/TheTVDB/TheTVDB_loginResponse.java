package run_ffmpeg.TheTVDB;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

public class TheTVDB_loginResponse
{
	public String status = "" ;
	public Map< String, String > data = new HashMap< String, String >() ;	

	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
