package run_ffmpeg.TheMovieDB;

import java.util.List;

import com.google.gson.Gson;

public class TheMovieDB_searchResponse
{
	public Integer page = null ;
	public List< TheMovieDB_searchResult > results = null ;
	public Integer total_pages = null ;
	public Integer total_results = null ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
