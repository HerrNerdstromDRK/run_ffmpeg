package run_ffmpeg;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

public class TheTVDB_loginResponse
{
	public String status = "" ;
	public Map< String, String > data = new HashMap< String, String >() ;	

	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}
