package run_ffmpeg;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Data storage class for the move thread infrastructure.
 * @author Dan
 *
 */
class MoveFileInfo
{
	protected String sourceFileNameWithPath = null ;
	protected String destinationFileNameWithPath = null ;
	protected File sourceFile = null ;
	protected Path sourceFileDirectoryPath = null ;
	protected Path sourceFilePath = null ;
	
	protected File destinationFile = null ;
	protected Path destinationFileDirectoryPath = null ;
	protected Path destinationFilePath = null ;
	
	public MoveFileInfo( final String sourceFileNameWithPath, final String destinationFileNameWithPath )
	{
		this.sourceFileNameWithPath = sourceFileNameWithPath ;
		this.destinationFileNameWithPath = destinationFileNameWithPath ;
		
		setSourceFile( new File( getSourceFileNameWithPath() ) ) ;
		setSourceFileDirectoryPath( Paths.get( getSourceFile().getParent() ) ) ;
		setSourceFilePath( Paths.get( getSourceFileNameWithPath() ) ) ;
		
		setDestinationFile( new File( getDestinationFileNameWithPath() ) ) ;
		setDestinationFileDirectoryPath( Paths.get( getDestinationFile().getParent() ) ) ;
		setDestinationFilePath( Paths.get( getDestinationFileNameWithPath() ) ) ;
	}

	protected File getDestinationFile()
	{
		return destinationFile ;
	}

	protected Path getDestinationFileDirectoryPath()
	{
		return destinationFileDirectoryPath ;
	}

	protected String getDestinationFileNameWithPath()
	{
		return destinationFileNameWithPath ;
	}

	protected Path getDestinationFilePath()
	{
		return destinationFilePath ;
	}

	protected File getSourceFile()
	{
		return sourceFile ;
	}

	protected Path getSourceFileDirectoryPath()
	{
		return sourceFileDirectoryPath ;
	}

	protected String getSourceFileNameWithPath()
	{
		return sourceFileNameWithPath ;
	}

	protected Path getSourceFilePath()
	{
		return sourceFilePath ;
	}

	protected void setDestinationFile( File destinationFile )
	{
		this.destinationFile = destinationFile ;
	}

	protected void setDestinationFileDirectoryPath( Path destinationFileDirectoryPath )
	{
		this.destinationFileDirectoryPath = destinationFileDirectoryPath ;
	}

	protected void setDestinationFilePath( Path destinationFilePath )
	{
		this.destinationFilePath = destinationFilePath ;
	}

	protected void setSourceFile(File sourceFile)
	{
		this.sourceFile = sourceFile ;
	}

	protected void setSourceFileDirectoryPath( Path sourceFileDirectoryPath )
	{
		this.sourceFileDirectoryPath = sourceFileDirectoryPath ;
	}

	protected void setSourceFilePath(Path sourceFilePath)
	{
		this.sourceFilePath = sourceFilePath ;
	}
}