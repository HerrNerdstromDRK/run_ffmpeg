package run_ffmpeg.TheTVDB;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

public class TheTVDB_seriesEpisodesClass 
{
	public String status = "" ;
	public dataClass data = new dataClass() ;
	public TheTVDB_linksClass links = new TheTVDB_linksClass() ;

	public class dataClass
	{
		public seriesClass series = new seriesClass() ;
		public List< TheTVDB_episodeClass > episodes = new ArrayList< TheTVDB_episodeClass >() ;
	}
	
	public class seriesClass
	{
		public List< TheTVDB_aliasClass > aliases = new ArrayList< TheTVDB_aliasClass >() ;
		public Integer averageRuntime = null ;
		public String country = "" ;
		public Integer defaultSeasonType = null ;
		public String firstAired = "" ;
		public Integer id = null ;
		public String image = "" ;
		public Boolean isOrderRandomized = null ;
		public String lastAired = "" ;
		public String lastUpdated = "" ;
		public String name = "" ;
		public List< String > nameTranslations = new ArrayList< String >() ;
		public String nextAired = "" ;
		public String originalCountry = "" ;
		public String originalLanguage = "" ;
		public String overview = "" ;
		public List< String > overviewTranslations = new ArrayList< String >() ;
		public Integer score = null ;
		public String slug = "" ;
		public TheTVDB_statusClass status = new TheTVDB_statusClass() ;
		public String year = "" ;
	}
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
