package run_ffmpeg.TheMovieDB;

import java.util.List;

import com.google.gson.Gson;

public class TheMovieDB_findResponse
{
	public List< TheMovieDB_movieResult > movie_results = null ;
	public List< TheMovieDB_personResult > person_results = null ;
	public List< TheMovieDB_tvResult > tv_results = null ;
	public List< TheMovieDB_tvEpisodeResult > tv_episode_results = null ;
	public List< TheMovieDB_tvSeasonResult > tv_season_results = null ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
