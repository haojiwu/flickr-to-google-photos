import requests
import json
import argparse

def get_flickr_photos(page):
    url = host + "/flickr/photo"
    params = {'token': flickr_token, 'secret': flickr_secret, 'page': page}
    r = requests.get(url=url, params=params, verify=False)
    return r.json()

def create_google_photos(flickr_photos_batch, force_unique):
    url = host + "/google/photo"
    params = {'refreshToken': google_refresh_token, 'forceUnique': force_unique}
    r = requests.post(url=url, json=flickr_photos_batch, params=params, verify=False)
    return r.json()

def create_google_photos_in_batch(flickr_photos, force_unique='false'):
    google_batch_num = 0
    failed_flickr_photo_ids = set()
    non_unique_flickr_photo_ids = set()
    flickr_photos_batchs = [flickr_photos[i:i + google_batch_size] for i in range(0, len(flickr_photos), google_batch_size)]
    for flickr_photos_batch in flickr_photos_batchs:
        print('google batch num: {}'.format(google_batch_num))
        print(json.dumps(flickr_photos_batch, indent=2))
        create_response = create_google_photos(flickr_photos_batch, force_unique)
        print(json.dumps(create_response, indent=2))

        fail_ids = set([r['sourceId'] for r in create_response if r['status'] == 'FAIL'])
        print('size of fail ids: {}'.format(len(fail_ids)))
        failed_flickr_photo_ids.update(fail_ids)

        non_unique_ids = set([r['sourceId'] for r in create_response if r['status'] == 'EXIST_CAN_NOT_CREATE'])
        print('size of non unique ids: {}'.format(len(non_unique_ids)))
        non_unique_flickr_photo_ids.update(non_unique_ids)

        google_batch_num = google_batch_num + 1

    ret = dict()
    ret['failed'] = [photo for photo in flickr_photos if photo['id'] in failed_flickr_photo_ids]
    ret['non_unique'] = [photo for photo in flickr_photos if photo['id'] in non_unique_flickr_photo_ids]

    return ret

# main()
parser = argparse.ArgumentParser()
parser.add_argument("--host", help='Host URL, like "https://localhost:8443" or "https://my-flickr-to-google-photos.onrender.com"', required=True)
parser.add_argument("--flickr-token", help='Flickr token from https://[HOST]/auth/flickr/complete, like "12345678901234567-1234abc5d6e7890f"', required=True)
parser.add_argument("--flickr-secret", help='Flickr secret from https://[HOST]/auth/flickr/complete, like "1fa234b56c78de90"', required=True)
parser.add_argument("--flickr-user-id", help='Flickr user id from https://[HOST]/auth/flickr/complete, like "12345678@N00"', required=True)
parser.add_argument("--google-refresh-token", help='Google refreshToken from https://[HOST]/auth/google/complete, like ' +
                        '"1//23-4a5BCD6Ef7GhIJKLMNOPQRStU-V8Wx9y0zaBCd12Efg3HiJKlMnoPQ_rStU4vWx1YZabc5DefgH6iJk7LmNOPQr8stUvwxyza"', required=True)

args = parser.parse_args()

host = args.host
flickr_token = args.flickr_token
flickr_secret = args.flickr_secret
flickr_user_id = args.flickr_user_id
google_refresh_token = args.google_refresh_token

photo_page = 1 # set to 1 to star from beginning, or the to other to resume from other page
google_batch_size = 50 # Google Photo API only allows max 50 as batch size

all_failed_flickr_photos = []
all_non_unique_flickr_photos = []
while True:
    response = get_flickr_photos(photo_page)
    #print(json.dumps(response, indent=2))
    print('photo_page: {}, hasNext: {}'.format(photo_page, response['hasNext']))
    flickr_photos = response['flickrPhotos']
    result = create_google_photos_in_batch(flickr_photos)
    all_failed_flickr_photos.extend(result['failed'])
    all_non_unique_flickr_photos.extend(result['non_unique'])
    if (response['hasNext'] == False): # should be False. Set to True if you only want to try first page
        break
    else:
        photo_page = photo_page + 1

print('start to retry failed flickr photo. size: {}'.format(len(all_failed_flickr_photos)))
retry_failed_result = create_google_photos_in_batch(all_failed_flickr_photos)
print("retry failed again but still failed photos:")
print(json.dumps(retry_failed_result['failed'], indent=2))
print("retry failed again but non unique photos:")
print(json.dumps(retry_failed_result['non_unique'], indent=2))

print('start to retry non unique flickr photo. size: {}'.format(len(all_non_unique_flickr_photos)))
retry_non_unique_result = create_google_photos_in_batch(all_non_unique_flickr_photos, 'true')
print("retry non unique again but still failed photos:")
print(json.dumps(retry_non_unique_result['failed'], indent=2))
print("retry non unique again but non unique photos:")
print(json.dumps(retry_non_unique_result['non_unique'], indent=2))
