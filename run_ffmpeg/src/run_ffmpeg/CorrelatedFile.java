package run_ffmpeg;

import java.util.ArrayList;
import java.util.List;

public class CorrelatedFile implements Comparable< CorrelatedFile >
{
public String fileName = null ;

/// These next two record the mkv and mp4 files names and are populated
/// exclusively when the database needs to record this CorrelatedFile.
public List< String > mkvFilesByName = new ArrayList< String >() ;
public List< String > mp4FilesByName = new ArrayList< String >() ;

private transient List< FFmpegProbeResult > mkvFilesByProbe = new ArrayList< FFmpegProbeResult >() ;
private transient List< FFmpegProbeResult > mp4FilesByProbe = new ArrayList< FFmpegProbeResult >() ;

public CorrelatedFile( String fileName )
{
	this.fileName = fileName ;
}

public String toString()
{
	String retMe = "{fileName:" + fileName
			+ "," ;
	for( String probeResult : mkvFilesByName )
	{
		retMe += probeResult + "," ;
	}
	for( String probeResult : mp4FilesByName )
	{
		retMe += probeResult + "," ;
	}
	retMe += "}" ;
	return retMe ;
}

public void addMKVFile( FFmpegProbeResult theMKVFileProbeResult )
{
	mkvFilesByProbe.add( theMKVFileProbeResult ) ;
	mkvFilesByName.add( theMKVFileProbeResult.getFilename() ) ;
}

public void addMP4File( FFmpegProbeResult theMKVFileProbeResult )
{
	mp4FilesByProbe.add( theMKVFileProbeResult ) ;
	mp4FilesByName.add(theMKVFileProbeResult.getFilename() ) ;
}

@Override
public int compareTo( CorrelatedFile rhs )
{
	return fileName.compareTo( rhs.fileName ) ;
}

}
