/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: C:\\Projects\\VVM\\app\\src\\main\\aidl\\com\\att\\mobile\\android\\vvm\\control\\ATTM\\IRemoteATTMessagesService.aidl
 */
package com.att.mobile.android.vvm.control.ATTM;
public interface IRemoteATTMessagesService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.att.mobile.android.vvm.control.ATTM.IRemoteATTMessagesService
{
private static final java.lang.String DESCRIPTOR = "com.att.mobile.android.vvm.control.ATTM.IRemoteATTMessagesService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.att.mobile.android.vvm.control.ATTM.IRemoteATTMessagesService interface,
 * generating a proxy if needed.
 */
public static com.att.mobile.android.vvm.control.ATTM.IRemoteATTMessagesService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.att.mobile.android.vvm.control.ATTM.IRemoteATTMessagesService))) {
return ((com.att.mobile.android.vvm.control.ATTM.IRemoteATTMessagesService)iin);
}
return new com.att.mobile.android.vvm.control.ATTM.IRemoteATTMessagesService.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_isLegacyVVMUibCompatible:
{
data.enforceInterface(DESCRIPTOR);
boolean _result = this.isLegacyVVMUibCompatible();
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_getVVMConnectivityCredentials:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _result = this.getVVMConnectivityCredentials();
reply.writeNoException();
reply.writeString(_result);
return true;
}
case TRANSACTION_getFileFromMessage:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
byte[] _result = this.getFileFromMessage(_arg0);
reply.writeNoException();
reply.writeByteArray(_result);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.att.mobile.android.vvm.control.ATTM.IRemoteATTMessagesService
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
@Override public boolean isLegacyVVMUibCompatible() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_isLegacyVVMUibCompatible, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public java.lang.String getVVMConnectivityCredentials() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getVVMConnectivityCredentials, _data, _reply, 0);
_reply.readException();
_result = _reply.readString();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public byte[] getFileFromMessage(java.lang.String fileName) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
byte[] _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(fileName);
mRemote.transact(Stub.TRANSACTION_getFileFromMessage, _data, _reply, 0);
_reply.readException();
_result = _reply.createByteArray();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
}
static final int TRANSACTION_isLegacyVVMUibCompatible = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_getVVMConnectivityCredentials = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_getFileFromMessage = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
}
public boolean isLegacyVVMUibCompatible() throws android.os.RemoteException;
public java.lang.String getVVMConnectivityCredentials() throws android.os.RemoteException;
public byte[] getFileFromMessage(java.lang.String fileName) throws android.os.RemoteException;
}
