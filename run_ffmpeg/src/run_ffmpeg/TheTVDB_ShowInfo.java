package run_ffmpeg;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

public class TheTVDB_ShowInfo
{
	protected Integer showId = -1 ;
	protected Map< Integer, TheTVDB_SeasonInfo > seasonInfos = new HashMap< Integer, TheTVDB_SeasonInfo >() ;
	
	public TheTVDB_ShowInfo( final Integer showId )
	{
		this.showId = showId ;
	}
	
	public void addSeason( final int seasonNumber, final TheTVDB_SeasonInfo seasonInfo )
	{
		assert( seasonNumber >= 0 ) ;
		assert( seasonInfo != null ) ;

		seasonInfos.put( seasonNumber, seasonInfo ) ;
	}

	public int getNumSeasons()
	{
		return seasonInfos.size() ;
	}
	
	public TheTVDB_SeasonInfo getSeasonInfo( final Integer seasonNumber )
	{
		assert( seasonNumber != null ) ;
		final TheTVDB_SeasonInfo theInfo = seasonInfos.get( seasonNumber ) ;
		return theInfo ;
	}
	
	public String getEpisodeName( final int seasonNumber, final int episodeNumber )
	{
		final TheTVDB_SeasonInfo seasonInfo = seasonInfos.get( Integer.valueOf( seasonNumber ) ) ;
		if( null == seasonInfo )
		{
			return "" ;
		}
		return seasonInfo.getEpisodeName( Integer.valueOf( episodeNumber ) ) ;
	}
	
	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}
