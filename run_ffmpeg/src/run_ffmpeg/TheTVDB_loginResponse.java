package run_ffmpeg;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class TheTVDB_loginResponse
{
	public String status = "" ;
	public Map< String, String > data = new HashMap< String, String >() ;	

	public String toString()
	{
		String retMe = "{ status: "  + ", " + StringUtils.join( data, "," ) + "}" ;
		return retMe ;
	}
}
