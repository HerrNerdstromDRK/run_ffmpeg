package run_ffmpeg.TheTVDB;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

public class TheTVDB_seasonClass
{
	public Integer id = null ;
	public String image = "" ;
	public Integer imageType = null ;
	public String lastUpdated = "" ;
	public String name = "" ;
	public List< String > nameTranslations = new ArrayList< String >() ;
	public Integer number = null ;
	public List< String > overviewTranslations = new ArrayList< String >() ;
	public Integer seriesID = null ;
	public TheTVDB_typeClass type = new TheTVDB_typeClass() ;
	public String year = "" ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
