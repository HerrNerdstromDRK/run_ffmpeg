package run_ffmpeg;

public class TheTVDB_loginRequest
{
	public String apikey = "" ;
	public String pin = "string" ;

	public TheTVDB_loginRequest()
	{}
	
	public TheTVDB_loginRequest( final String apikey, final String pin )
	{
		this.apikey = apikey ;
		this.pin = pin ;
	}
}
