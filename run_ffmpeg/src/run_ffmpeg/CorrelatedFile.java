package run_ffmpeg;

import java.util.ArrayList;
import java.util.List;

/**
 * Structure that stores information about a file attached to a movie or tv show.
 * The idea is that we need to record and match each mkv file with its corresponding
 *  mp4 file.
 * Instances of this class are intended to be stored in the database.
 * @author Dan
 */
public class CorrelatedFile implements Comparable< CorrelatedFile >
{
	/// Name of the file in question, without extension or path.
	/// For example: "Making Of-behindthescenes"
	public String fileName = null ;
	
	/// Record if an mkv or mp4 file is missing for use in the database
	/// and presentation to the user.
	/// These variables are only updated in the normalizeMKVAndMP4Files() method.
	public boolean missingMKVFile = false ;
	public boolean missingMP4File = false ;

	/// These next two record the mkv and mp4 files names and are populated
	/// exclusively when the database needs to record this CorrelatedFile.
	/// filenames here are stored as their shortened file name, for example
	/// "MKV_1/Transformers (2001)/Transformers (2001).mkv"
	public List< String > mkvFilesByName = new ArrayList< String >() ;
	public List< String > mp4FilesByName = new ArrayList< String >() ;

	private transient List< FFmpegProbeResult > mkvFilesByProbe = new ArrayList< FFmpegProbeResult >() ;
	private transient List< FFmpegProbeResult > mp4FilesByProbe = new ArrayList< FFmpegProbeResult >() ;

	public CorrelatedFile( String fileName )
	{
		this.fileName = fileName ;
	}
	
	public CorrelatedFile()
	{}

	/**
	 * Record an mkv file for this correlated file.
	 * Replace an empty mkv file if present.
	 * @param theMKVFileProbeResult
	 */
	public void addOrUpdateMKVFile( FFmpegProbeResult theMKVFileProbeResult )
	{
		final String shortenedFileName = theMKVFileProbeResult.getFileNameShort().replace( Common.getMissingFilePreExtension(), "" ) ;
		
		// Remove substitute names included from the mkvFilesByName structure
		removeFilesByName( mkvFilesByName, shortenedFileName ) ;
		removeFilesByName( mkvFilesByName, Common.getMissingFileSubstituteName() ) ;
		
		// Remove probe results for the same name.
		removeProbeResult( mkvFilesByProbe, theMKVFileProbeResult ) ;
		
		mkvFilesByName.add( shortenedFileName ) ;
		mkvFilesByProbe.add( theMKVFileProbeResult ) ;
	}
	
	/**
	 * Record an mp4 file for this correlated file.
	 * @param theMP4FileProbeResult
	 */
	public void addOrUpdateMP4File( FFmpegProbeResult theMP4FileProbeResult )
	{
		final String shortenedFileName = theMP4FileProbeResult.getFileNameShort() ;
		
		// Remove substitute names included from the mkvFilesByName structure
		removeFilesByName( mp4FilesByName, shortenedFileName ) ;
		removeFilesByName( mp4FilesByName, Common.getMissingFileSubstituteName() ) ;
		
		// Remove probe results for the same name.
		removeProbeResult( mp4FilesByProbe, theMP4FileProbeResult ) ;
		
		mp4FilesByName.add( shortenedFileName ) ;
		mp4FilesByProbe.add( theMP4FileProbeResult ) ;
	}

	@Override
	public int compareTo( CorrelatedFile rhs )
	{
		return fileName.compareTo( rhs.fileName ) ;
	}

	public FFmpegProbeResult findLargestFile()
	{
		FFmpegProbeResult largestFile = null ;
		for( FFmpegProbeResult theProbeResult : mkvFilesByProbe )
		{
			if( null == largestFile )
			{
				largestFile = theProbeResult ;
			}
			else if( theProbeResult.getSize() > largestFile.getSize() )
			{
				largestFile = theProbeResult ;
			}
		}
		for( FFmpegProbeResult theProbeResult : mp4FilesByProbe )
		{
			if( null == largestFile )
			{
				largestFile = theProbeResult ;
			}
			else if( theProbeResult.getSize() > largestFile.getSize() )
			{
				largestFile = theProbeResult ;
			}
		}
		return largestFile ;
	}

	/**
	 * This method will fill any empty mkv or mp4 files with "(none)" to ensure the
	 * number of mkv and mp4 files matches for presentation.
	 */
	public void normalizeMKVAndMP4Files()
	{
		// The first time this method is run, it may populate either mkv or mp4 file list
		// with the missing file substitute.
		// Each subsequent time this method runs will already be populated with the missing
		// file substitute.
		missingMKVFile = false ;
		missingMP4File = false ;
		
		while( mkvFilesByName.size() < mp4FilesByName.size() )
		{
			missingMKVFile = true ;
			mkvFilesByName.add( Common.getMissingFileSubstituteName() ) ;
		}
		if( !missingMKVFile )
		{
			// Look for the instance where the mkvFilesByName may have a missing file from
			// the first run.
			for( String theFile : mkvFilesByName )
			{
				if( theFile.equals( Common.getMissingFileSubstituteName() ) )
				{
					missingMKVFile = true ;
				}
			}
		}
		
		while( mp4FilesByName.size() < mkvFilesByName.size() )
		{
			missingMP4File = true ;
			mp4FilesByName.add( Common.getMissingFileSubstituteName() ) ;
		}
		if( !missingMP4File )
		{
			// Look for the instance where the mkvFilesByName may have a missing file from
			// the first run.
			for( String theFile : mp4FilesByName )
			{
				if( theFile.equals( Common.getMissingFileSubstituteName() ) )
				{
					missingMP4File = true ;
				}
			}
		}
	}

	public void removeProbeResult( List< FFmpegProbeResult > probeResultList, final FFmpegProbeResult removeMe )
	{
		for( int index = 0 ; index < probeResultList.size() ; ++index )
		{
			FFmpegProbeResult checkProbeResult = probeResultList.get( index ) ;
			if( checkProbeResult.getFileNameWithPath().equals( removeMe.getFileNameWithPath() ) )
			{
				// Found match.
				probeResultList.remove( index ) ;

				// Should be only one item with the file name, but just to be certain
				// check again.
				index = -1 ;
			}
		}
	}
	
	public void removeFilesByName( List< String > fileNameList, final String removeMe )
	{
		// First, delete any mkv substitute files
		for( int index = 0 ; index < fileNameList.size() ; ++index )
		{
			final String theFileName = fileNameList.get( index ) ;
			if( theFileName.equals( removeMe ) )
			{
				fileNameList.remove( index ) ;
				// Removing an item while walking through a linear structure tends to mess
				// up the iteration.
				// Reset the index to -1 (which will then be incremented to 0 at the end of this loop)
				// to restart the search.
				index = -1 ;
			}
		}
	}

	public String getFileName()
	{
		return fileName;
	}
	
	/**
	 * Would use an iterator here but having a problem serializing with mongoDB.
	 * @return
	 */
//	public List< String > getMKVFilesByName()
//	{
//		return mkvFilesByName ;
//	}
	
	/**
	 * Would use an iterator here but having a problem serializing with mongoDB.
	 * @return
	 */
//	public List< String > getMP4FilesByName()
//	{
//		return mp4FilesByName ;
//	}

//	public Iterator< String > getMKVFilesIterator()
//	{
//		return mkvFilesByName.iterator() ;
//	}
//	
//	public Iterator< FFmpegProbeResult > getMKVFilesProbeIterator()
//	{
//		return mkvFilesByProbe.iterator() ;
//	}
//
//	public Iterator< FFmpegProbeResult > getMP4FilesProbeIterator()
//	{
//		return mp4FilesByProbe.iterator() ;
//	}
//	
//	public Iterator< String > getMP4FilesIterator()
//	{
//		return mp4FilesByName.iterator() ;
//	}
	
	public int getNumberOfMKVFiles()
	{
		return mkvFilesByName.size() ;
	}

	public int getNumberOfMP4Files()
	{
		return mp4FilesByName.size() ;
	}

	/**
	 * Return true if this CorrelatedFile is missing a file.
	 * This will mean that the number of mkv files is different from the
	 *  number of mp4 files.
	 * @return
	 */
	public boolean isMissingFile()
	// TODO: Make this a variable instead of a method.
	{
		return (isMissingMKVFile() || isMissingMP4File() ) ;
	}

	public boolean isMissingMKVFile()
	{
		return missingMKVFile;
	}

	public boolean isMissingMP4File()
	{
		return missingMP4File;
	}

	public void setFileName(String fileName)
	{
		this.fileName = fileName;
	}

	public void setMissingMKVFile(boolean missingMKVFile)
	{
		this.missingMKVFile = missingMKVFile;
	}

	public void setMissingMP4File(boolean missingMP4File)
	{
		this.missingMP4File = missingMP4File;
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

}
