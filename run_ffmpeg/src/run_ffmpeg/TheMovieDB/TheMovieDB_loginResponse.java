package run_ffmpeg.TheMovieDB;

import com.google.gson.Gson;

public class TheMovieDB_loginResponse
{
	public Integer status_code = null ;
	public String status_message = "" ;
	public String success = "" ;

	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
