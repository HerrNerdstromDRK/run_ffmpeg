package run_ffmpeg;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

public class TheTVDB_SeasonInfo
{
	protected Integer seasonNumber = -1 ;
	protected Integer numEpisodes = -1 ;
	protected Map< Integer, String > episodeNames = new HashMap< Integer, String >() ;
	
	public TheTVDB_SeasonInfo( final Integer seasonNumber, final Integer numEpisodes )
	{
		this.seasonNumber = seasonNumber ;
		this.numEpisodes = numEpisodes ;
	}
	
	public void addEpisode( final int episodeNumber, final String episodeName )
	{
		assert( episodeNumber >= 0 ) ;
		assert( episodeName != null ) ;
		assert( !episodeName.isBlank() ) ;
		
		episodeNames.put( Integer.valueOf( episodeNumber ), episodeName ) ;
	}
	
	public String getEpisodeName( final Integer episodeNumber )
	{
		assert( episodeNumber.intValue() >= 0 ) ;
		final String episodeName = episodeNames.get( episodeNumber ) ;
		return episodeName ;
	}
	
	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}
