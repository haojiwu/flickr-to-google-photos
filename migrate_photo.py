import requests
import json

host = "https://localhost:8443"
flickr_token = "my flickr token from https://localhost:8443/auth/flickr/complete"
flickr_secret = "my flickr secret from https://localhost:8443/auth/flickr/complete"
flickr_user_id = "my flickr user id from https://localhost:8443/auth/flickr/complete"
google_refresh_token = "my google refreshToken from https://localhost:8443/auth/google/complete"

def get_flickr_photos(page):
    url = host + "/flickr/photo"
    params = {'token': flickr_token, 'secret': flickr_secret, 'page': page}
    r = requests.get(url=url, params=params, verify=False)
    return r.json()

def create_google_photos(flickr_photos_batch):
    url = host + "/google/photo"
    params = {'refreshToken': google_refresh_token}
    r = requests.post(url=url, json=flickr_photos_batch, params=params, verify=False)
    return r.json()

photo_page = 1 # set to 1 to star from beginning, or the to other to resume from other page
google_batch_size = 50 # Google Photo API only allows max 50 as batch size

def create_google_photos_in_batch(flickr_photos):
    google_batch_num = 0
    fail_flickr_photo_ids = set()
    flickr_photos_batchs = [flickr_photos[i:i + google_batch_size] for i in range(0, len(flickr_photos), google_batch_size)]
    for flickr_photos_batch in flickr_photos_batchs:
        print('google batch num: {}'.format(google_batch_num))
        print(json.dumps(flickr_photos_batch, indent=2))
        create_response = create_google_photos(flickr_photos_batch)
        print(json.dumps(create_response, indent=2))

        fail_ids = set([r['sourceId'] for r in create_response if r['status'] == 'FAIL'])
        print('size of fail ids: {}'.format(len(fail_ids)))
        fail_flickr_photo_ids.update(fail_ids)
        google_batch_num = google_batch_num + 1

    return [photo for photo in flickr_photos if photo['id'] in fail_flickr_photo_ids]

all_failed_flickr_photos = []
while True:
    response = get_flickr_photos(photo_page)
    #print(json.dumps(response, indent=2))
    print('photo_page: {}, hasNext: {}'.format(photo_page, response['hasNext']))
    flickr_photos = response['flickrPhotos']
    failed_flickr_photos = create_google_photos_in_batch(flickr_photos)
    all_failed_flickr_photos.extend(failed_flickr_photos)
    if (response['hasNext'] == False): # should be False. Set to True if you only want to try first page
        break
    else:
        photo_page = photo_page + 1

print('start to retry create failed flickr photo. size: {}'.format(len(all_failed_flickr_photos)))
failed_again_flickr_photos = create_google_photos_in_batch(all_failed_flickr_photos)
print("failed again photos:")
print(json.dumps(failed_again_flickr_photos, indent=2))  # need troubleshooting. Either Flickr service down or other issues
