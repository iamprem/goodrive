GooDrive
========

GooDrive is trying to be the **most functional Google Drive Client** available today for linux. We already have few Google Drive client programs([Grive](https://github.com/Grive/grive), [Grive2](https://github.com/vitalif/grive2), [GoogleDriveJavaClient] (https://github.com/edgefox/GoogleDriveJavaClient)) written 
for linux by great programmers and they definitely inspired me a lot. The reason i started this project is, I found that existing Google Drive clients lack key 
features and those are not supported by the developers recently and also it is very unlikely to see an official version 
of Google Drive client on linux any time soon.

Like i said, I wanted it to be most functional **rather than most efficient for now**. So i will be tweaking up the code
constantly to improve efficiency. This can be considered as a "functional prototype rather than considering as a beta
version".

Feature Supported:
------------------

 - [x] Monitor for continuous changes in local directory and upload the changes.
 - [x] Sync occurs once for every minute.
 - [x] Use file's modified time to track changes.
 - [x] Upload the whole file instead of delta file on modification.
 - [x] Renaming a file in local means, DELETE and CREATE operations.
 - [x] Moving files from one folder to other inside the drive path cause deletion and re-upload of files.
 - [x] File's with same name in Drive will be renamed by numbering.
 - [x] Long filenames will be truncated to 255 characters.
 
Unsupported Features:
---------------------
 
 - [ ] Google special file types that opens in a browser(Google docs, sheets and so on)
 - [ ] Symbolic links for files (i.e multiple parents for a file)
 - [ ] Share files with an user
 - [ ] Icons that show the sync status of the file
 - [ ] Files shared to you by someone
 
Installation Instruction:(Just to run, not finalized)
-----------------------------------------------------

* Clone the project.
* Open in eclipse or IntelliJ as a gradle project.
* Run the application.
* Authenticate it using your google account.
* Happy trying.

Note: Sooner i'll make it easy to install.

**Send me your feedback to mprem.dev@gmail.com**

**Thanks :)**
