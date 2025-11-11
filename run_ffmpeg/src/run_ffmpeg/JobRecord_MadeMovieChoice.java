package run_ffmpeg;

import com.google.gson.Gson;

public class JobRecord_MadeMovieChoice
{
	public String inputDirectoryAbsolutePath = "" ;
	public Long tmdbID = null ;
	
	public JobRecord_MadeMovieChoice()
	{}

	public JobRecord_MadeMovieChoice( final String inputDirectoryAbsolutePath, final Long tmdbID )
	{
		this.inputDirectoryAbsolutePath = inputDirectoryAbsolutePath ;
		this.tmdbID = tmdbID ;
	}
	
	public String getInputDirectoryAbsolutePath()
	{
		return inputDirectoryAbsolutePath ;
	}

	public Long getTmdbID()
	{
		return tmdbID ;
	}

	public void setInputDirectoryAbsolutePath( String movieID )
	{
		this.inputDirectoryAbsolutePath = movieID ;
	}

	public void setTmdbID( Long tmdbID )
	{
		this.tmdbID = tmdbID ;
	}

	public String toString()
	{
		Gson gsonRequest= new Gson() ;
		final String jsonRequest = gsonRequest.toJson( this ) ;
		return jsonRequest.toString() ;
	}
}
