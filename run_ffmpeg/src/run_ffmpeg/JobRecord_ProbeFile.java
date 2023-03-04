package run_ffmpeg;

public class JobRecord_ProbeFile
{
	public String fileNameWithPath = null ;
	
	public JobRecord_ProbeFile()
	{}
	
	public JobRecord_ProbeFile( final String fileNameWithPath )
	{
		this.fileNameWithPath = fileNameWithPath ;
	}
	
	public String toString()
	{
		return "{" + fileNameWithPath + "}" ;
	}
}
