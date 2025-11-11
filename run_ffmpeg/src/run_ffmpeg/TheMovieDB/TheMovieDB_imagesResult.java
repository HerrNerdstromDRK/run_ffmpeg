package run_ffmpeg.TheMovieDB;

import java.util.List;

import com.google.gson.Gson;

public class TheMovieDB_imagesResult
{
	public String base_url = "" ;
	public String secure_base_url = "" ;
	public List< String > backdrop_sizes = null ;
	public List< String > logo_sizes = null ;
	public List< String > poster_sizes = null ;
	public List< String > profile_sizes = null ;
	public List< String > still_sizes = null ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
