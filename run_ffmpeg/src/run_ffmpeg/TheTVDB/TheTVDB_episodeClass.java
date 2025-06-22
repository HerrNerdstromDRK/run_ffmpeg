package run_ffmpeg.TheTVDB;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

public class TheTVDB_episodeClass
{
	public BigInteger id = null ;
	public BigInteger seriesId = null ;
	public String name = "" ;
	public String aired = "" ;
	public Integer runtime = null ;
	public List< String > nameTranslations = new ArrayList< String >() ;
	public String overview = "" ;
	public List< String > overviewTranslations = new ArrayList< String >() ;
	public String image = "" ;
	public Integer imageType = null ;
	public Integer isMovie = null ;
	public String seasons = "" ;
	public Integer number = null ;
	public Integer absoluteNumber = null ;
	public Integer seasonNumber = null ;
	public String seasonName = "" ;
	public String lastUpdated = "" ;
	public String finalType = "" ;
	public String year = "" ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
