package run_ffmpeg.TheMovieDB;

import com.google.gson.Gson;

public class TheMovieDB_personResult
{
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
