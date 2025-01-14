package run_ffmpeg;

import java.util.ArrayList;
import java.util.List;

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
}
