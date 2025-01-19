package run_ffmpeg;

import java.math.BigInteger;

import com.google.gson.Gson;

public class TheTVDB_statusClass
{
	public BigInteger id = null ;
	public Boolean keepUpdated = null ;
	public String name = "" ;
	public String recordType = "" ;
	
	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}
