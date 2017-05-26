# -*- coding: utf-8 -*-
"""
Created on Mon Dec 26 17:05:12 2016

My Activities Main Python Module

This is the main entry point for the real-time Python analytics.

"""

from client import Client
import json
from quaternions import qv_mult
from madgwick_py.madgwickahrs import MadgwickAHRS
import numpy as np
import pickle
from peakdetect import peakdetect
from features import extract_features

# instantiate the client, passing in a valid user ID:
user_id = "91.e6.eb.8d.65.ee.a5.e3.5a.b3"
c = Client(user_id)

anchor = None
window_size= 120 #1200
dist = np.zeros((window_size,))
accel = np.zeros((window_size, 3))
gyro = np.zeros((window_size, 3))
Q = np.zeros((window_size, 4))
index = 0

# TODO
#with open('classifier.pickle', 'rb') as f:
#    clf = pickle.load(f)

fuse = MadgwickAHRS(sampleperiod=1./72)

def on_pill_gesture_detected():
    header = {'user_id' : user_id, message_type : 'analytics_message', 'message_version' : 'version_1.0'}
    metadata = {'analytics_type' : 'gesture_detected'}
    payload = {'t' : time.time(), 'bottle_uuid' : 'THIS0IS0A0UUID'}
    message = {'header' : header, 'metadata' : metadata, 'payload' : payload}
    c.send_socket.send(json.dumps(message) + "\n")

def predict():
    print "buffer filled"
    global dist
    global accel
    global gyro
    global Q
    global index
    
    on_pill_gesture_detected()
    return # TODO actually predict
    
#    print "predicting"
    
    max_peaks, min_peaks = peakdetect(dist, lookahead=50)
    for p in min_peaks:
        p.append(0)
    for p in max_peaks:
        p.append(1)
                
    critical_points = min_peaks + max_peaks
    critical_points.sort(key=lambda x: x[0])
    
#    print "found " + str(len(critical_points)) + " critical points"
    
    prev_max = False
    prev_prev_min = False
    start = 0
    for i,p in enumerate(critical_points):
        # trough-peak-trough pattern
#            print p[-1], prev_max, prev_prev_min
        if p[-1] == 0 and prev_max and prev_prev_min:
            end = p[0]
            x = extract_features(accel[start:end,:], gyro[start:end,:], dist[start:end], Q[start:end,:])
            print x.shape
            classes = ["Nothing", "Pill", "Water"]
            print classes[np.argmax(clf.decision_function(x.reshape((1,-1))))], p
        elif p[-1] == 1:
            start = p[0]
        if i > 0:
            prev_prev_min = not prev_max
        prev_max = p[-1] == 1
        
    prev_min = False
    prev_prev_max = False
    start = 0
    for i,p in enumerate(critical_points):
        # peak-trough-peak pattern
#            print p[-1], prev_max, prev_prev_min
        if p[-1] == 1 and prev_min and prev_prev_max:
            end = p[0]
            x = extract_features(accel[start:end,:], gyro[start:end,:], dist[start:end], Q[start:end,:])
            print x.shape
            classes = ["Nothing", "Pill", "Water"]
            print classes[np.argmax(clf.decision_function(x.reshape((1,-1))))], p
        elif p[-1] == 0:
            start = p[0]
        if i > 0:
            prev_prev_max = not prev_min
        prev_min = p[-1] == 1
    
def compute_trajectory(data, send_notification):
    global anchor
    global dist
    global accel
    global gyro
    global Q
    global index
    
    aX = data['aX']
    aY = data['aY']
    aZ = data['aZ']
    gX = data['gX']
    gY = data['gY']
    gZ = data['gZ']
#    print [data['gX'], data['gY'], data['gZ']], [data['aX'], data['aY'], data['aZ']]
    fuse.update_imu(np.asarray([gX, gY, gZ])*np.pi / 180., np.asarray([aX, aY, aZ]))
    position = qv_mult(fuse.quaternion.q, (1, 0, 0))
    position = np.multiply(position, [-1,-1,1]) # TODO do I need to do this??

    accel[index,:] = [aX, aY, aZ]
    gyro[index,:] = [gX, gY, gZ]
    Q[index,:] = fuse.quaternion.q
    if anchor == None:
        anchor = position
        dist[index] = 0
    else:
        d=np.sqrt(np.sum(np.square(np.subtract(position, anchor))))
        dist[index] = d
    index+=1 
        
    if index >= window_size:
        predict()
        index=0
            
    
            
    c.send_socket.send(json.dumps({'user_id' : user_id, 'sensor_type' : 'SENSOR_TRAJECTORY', 'data': {'t' : data['t'], 'x' : position[0], 'y' : position[1], 'z' : position[2]}}) + "\n")
    c.send_socket.send(json.dumps({'user_id' : user_id, 'sensor_type' : 'SENSOR_DISTANCE', 'data': {'t' : data['t'], 'distance' : dist[index]}}) + "\n")

c.map_data_to_function("SENSOR_ACCEL_GYRO", compute_trajectory)

# connect to the server to begin:
c.connect()