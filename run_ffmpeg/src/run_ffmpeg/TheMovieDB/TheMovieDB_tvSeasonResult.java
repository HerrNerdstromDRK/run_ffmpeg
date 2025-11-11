package run_ffmpeg.TheMovieDB;

import com.google.gson.Gson;

public class TheMovieDB_tvSeasonResult
{
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
