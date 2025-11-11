package run_ffmpeg.TheMovieDB;

import java.util.List;

import com.google.gson.Gson;

public class TheMovieDB_movieResult
{
	public Boolean adult = null ;
	public String backdrop_path = "" ;
	public Long id = null ;
	public String title = "" ;
	public String original_language = "" ;
	public String original_title = "" ;
	public String overview = "" ;
	public String poster_path = "" ;
	public List< Long > genre_ids = null ;
	public Double popularity = null ;
	public String release_date = "" ;
	public Boolean video = null ;
	public Double vote_average = null ;
	public Integer vote_count = null ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
