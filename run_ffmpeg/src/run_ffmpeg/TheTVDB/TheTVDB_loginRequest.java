package run_ffmpeg.TheTVDB;

import com.google.gson.Gson;

public class TheTVDB_loginRequest
{
	public String apikey = "" ;
	public String pin = "string" ;

	public TheTVDB_loginRequest()
	{}
	
	public TheTVDB_loginRequest( final String apikey, final String pin )
	{
		this.apikey = apikey ;
		this.pin = pin ;
	}
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
