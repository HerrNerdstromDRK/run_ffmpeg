package run_ffmpeg.TheMovieDB;

import java.util.List;

import com.google.gson.Gson;

public class TheMovieDB_tvResult
{
	public Boolean adult = null ;
	public String backdrop_path = "" ;
	public Long id = null ;
	public String name = "" ;
	public String original_name = "" ;
	public String overview = "" ;
	public String poster_path = "" ;
	public String media_type = "" ;
	public String original_language = "" ;
	public List< Long > genre_ids = null ;
	public Double popularity = null ;
	public String first_air_date= "" ;
	public Double vote_average = null ;
	public Integer vote_count = null ;
	public List< String > origin_country = null ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
