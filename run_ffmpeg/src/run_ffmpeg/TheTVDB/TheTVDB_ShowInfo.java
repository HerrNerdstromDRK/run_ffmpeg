package run_ffmpeg.TheTVDB;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

public class TheTVDB_ShowInfo
{
	protected Integer showId = -1 ;
	protected Map< Integer, TheTVDB_seasonInfo > seasonInfos = new HashMap< Integer, TheTVDB_seasonInfo >() ;
	
	public TheTVDB_ShowInfo( final Integer showId )
	{
		this.showId = showId ;
	}
	
	public void addSeason( final int seasonNumber, final TheTVDB_seasonInfo seasonInfo )
	{
		assert( seasonNumber >= 0 ) ;
		assert( seasonInfo != null ) ;

		seasonInfos.put( seasonNumber, seasonInfo ) ;
	}

	public int getNumSeasons()
	{
		return seasonInfos.size() ;
	}
	
	public TheTVDB_seasonInfo getSeasonInfo( final Integer seasonNumber )
	{
		assert( seasonNumber != null ) ;
		final TheTVDB_seasonInfo theInfo = seasonInfos.get( seasonNumber ) ;
		return theInfo ;
	}
	
	public String getEpisodeName( final int seasonNumber, final int episodeNumber )
	{
		final TheTVDB_seasonInfo seasonInfo = seasonInfos.get( Integer.valueOf( seasonNumber ) ) ;
		if( null == seasonInfo )
		{
			return "" ;
		}
		return seasonInfo.getEpisodeName( Integer.valueOf( episodeNumber ) ) ;
	}
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
