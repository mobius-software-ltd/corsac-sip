# Jain-SIP-RI Release Notes

### Tags

The folowing tags are used to categorize and state the scope of a change

* **security improvement** tags changes related to security
* **commercial** tags changes that are available only in the commercial RestcommOne product

# JSIP-RI Release Notes

## X.Y.Z version 2024-01-XX

### Release Unit/Integration Tests
http://X.X.X.X:8080/job/mobius-jsip-pipeline/job/mobius-8.0-netty/187/testReport/

### Performance Tests
https://docs.google.com/document/d/1bDfkcpPcQ0vQFVqCXaSV41wWxgTj5J5hENLg3oXgaoA/edit?usp=sharing

### New features
* Netty Framework Based Transport Layer
* Async and Non-Blocking Implementation serializing messages to application layer on Call ID
* Timers based on Mobius Timers Library

### Breaking Changes

* N/A

### Bug fixes

* N/A

## 7.1.1 version 2019-06-26

### Release Unit/Integration Tests
https://cxs.restcomm.com/job/TelScale-JAIN-SIP-7-MultiPipeline/job/ts2/16/

### New features
* N/A

### Breaking Changes

* N/A

### Bug fixes

* BS-2678: Thread-safe transaction data
    * Transaction data is now powered by a concurrent map to make it thread-safe.


## 7.1.0 version 2019-05-30

### Release Unit/Integration Tests
https://cxs.restcomm.com/job/TelScale-JAIN-SIP-7-MultiPipeline/job/ts2/14/

### New features
* **BS-2213:**
    * Transaction application data as key-value data store.
    * Added sub-types of ServerResponseInterface: TransactionResponseInterface and DialogResponseInterface to allow 
    fine-grained operations. 

### Breaking Changes

* N/A

### Bug fixes

* Removed SCTP module from default Maven profile
