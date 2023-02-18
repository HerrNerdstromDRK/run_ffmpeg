package run_ffmpeg;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

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

	/**
	 * Return true if this CorrelatedFile is missing a file.
	 * This will mean that the number of mkv files is different from the
	 *  number of mp4 files.
	 * @return
	 */
	public boolean isMissingFile()
	{
		return (mkvFilesByProbe.size() != mp4FilesByProbe.size()) ;
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

	public static String shortenFileName( final String inputName )
	{
		String retMe = "" ;
		StringTokenizer tokens = new StringTokenizer( inputName, "\\" ) ;
		
		// Walk through the tokens to build the shortened file name
		// Keep only "MKV_#" and the actual file name.
		while( tokens.hasMoreTokens() )
		{
			String nextToken = tokens.nextToken() ;
			if( nextToken.contains( "MKV_" ) || nextToken.contains( "MP4" ) )
			{
				retMe += nextToken.replace( "Archive", "" ) + "\\" ;
			}
			else if( nextToken.contains( ".mkv" ) || nextToken.contains( ".mp4" ) )
			{
				retMe += nextToken ;
			}
		}
		return retMe ;
	}

	public void addMKVFile( FFmpegProbeResult theMKVFileProbeResult )
	{
		mkvFilesByProbe.add( theMKVFileProbeResult ) ;
		final String shortenedFileName = shortenFileName( theMKVFileProbeResult.getFilename() ) ;
		mkvFilesByName.add( shortenedFileName ) ;
	}

	public void addMP4File( FFmpegProbeResult theMP4FileProbeResult )
	{
		mp4FilesByProbe.add( theMP4FileProbeResult ) ;
		final String shortenedFileName = shortenFileName( theMP4FileProbeResult.getFilename() ) ;
		mp4FilesByName.add( shortenedFileName ) ;
	}

	/**
	 * This method will fill any empty mkv or mp4 files with "(none)" to ensure the
	 * number of mkv and mp4 files matches for presentation.
	 */
	public void normalizeMKVAndMP4Files()
	{
		while( mkvFilesByName.size() < mp4FilesByName.size() )
		{
			mkvFilesByName.add( "(none)" ) ;
		}
		while( mp4FilesByName.size() < mkvFilesByName.size() )
		{
			mp4FilesByName.add( "(none)" ) ;
		}
	}

	@Override
	public int compareTo( CorrelatedFile rhs )
	{
		return fileName.compareTo( rhs.fileName ) ;
	}

}
