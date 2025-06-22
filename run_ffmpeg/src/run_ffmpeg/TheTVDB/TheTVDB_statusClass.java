package run_ffmpeg.TheTVDB;

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
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
