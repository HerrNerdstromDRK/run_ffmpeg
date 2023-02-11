package run_ffmpeg;

import java.util.Vector;

public class CorrelatedFile
{
public String fileName = null ;
public Vector< FFmpegProbeResult > mkvFiles = new Vector< FFmpegProbeResult >() ;
public Vector< FFmpegProbeResult > mp4Files = new Vector< FFmpegProbeResult >() ;

public CorrelatedFile( String fileName )
{
	this.fileName = fileName ;
}

public String toString()
{
	String retMe = "{fileName:" + fileName
			+ "," ;
	for( FFmpegProbeResult probeResult : mkvFiles )
	{
		retMe += probeResult.toString() + "," ;
	}
	for( FFmpegProbeResult probeResult : mp4Files )
	{
		retMe += probeResult.toString() + "," ;
	}
	retMe += "}" ;
	return retMe ;
}

}
